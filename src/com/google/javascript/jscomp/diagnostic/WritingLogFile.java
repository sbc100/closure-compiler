/*
 * Copyright 2019 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.diagnostic;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

/**
 * An implementation that adapts a {@link BufferedWriter} and writes to a real file.
 *
 * <p>This class is designed to be super-sourceable by J2CL, since JS environments often don't have
 * file APIs. Doing so enables logging statements to be checked-in.
 */
final class WritingLogFile extends LogFile {

  @MustBeClosed
  static LogFile create(Path file) {
    try {
      Path dir = file.getParent();
      Files.createDirectories(dir);
      CharsetEncoder encoder = UTF_8.newEncoder();
      // Allow the logged string to contain invalid Unicode (replace invalid character sequences
      // but don't throw). This is useful in cases where the source code contains invalid Unicode
      // (e.g., inside strings).
      encoder
          .onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE);
      return new WritingLogFile(
          new BufferedWriter(
              new OutputStreamWriter(
                  Files.newOutputStream(
                      file,
                      StandardOpenOption.CREATE,
                      StandardOpenOption.APPEND,
                      StandardOpenOption.WRITE),
                  encoder)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final BufferedWriter writer;

  private WritingLogFile(BufferedWriter writer) {
    this.writer = writer;
  }

  @Override
  public LogFile log(Object value) {
    return logInternal(value.toString());
  }

  @Override
  public LogFile log(String value) {
    return logInternal(value);
  }

  @Override
  public LogFile log(Supplier<String> value) {
    return logInternal(value.get());
  }

  @Override
  @FormatMethod
  public LogFile log(@FormatString String template, Object... values) {
    return logInternal(String.format(template, values));
  }

  @Override
  public LogFile logJson(Object value) {
    return logInternal(LogsGson.toJson(value));
  }

  @Override
  public LogFile logJson(Supplier<Object> value) {
    return logInternal(LogsGson.toJson(value.get()));
  }

  @Override
  public LogFile logJson(StreamedJsonProducer producer) {
    try (JsonWriter writer = new JsonWriter(this.writer)) {
      producer.writeJson(writer);
    } catch (IOException ex) {
      throw new AssertionError(ex);
    }
    return this;
  }

  private LogFile logInternal(String value) {
    try {
      // It's fine to pass a fully rendered string because we know we're going to use it by the
      // time this method is called.
      writer.append(value).append('\n');
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public void close() {
    try {
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

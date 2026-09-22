package com.ohpin.material.service;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface DocumentConverter {
  Path toPdf(Path source, Path workspace);

  int pageCount(Path pdf, Path workspace);

  void render(Path pdf, Path workspace, int count, Consumer<Path> onPage);
}

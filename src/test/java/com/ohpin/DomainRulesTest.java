package com.ohpin;

import static org.assertj.core.api.Assertions.*;

import com.ohpin.folder.entity.Folder;
import com.ohpin.material.entity.SourcePath;
import com.ohpin.question.entity.Point;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  private final UUID owner = UUID.randomUUID();

  @Test
  void folderUsesUnicodeCodePointsAndTrims() {
    assertThat(new Folder("  강의  ", 5).name()).isEqualTo("강의");
    assertThat(new Folder("😀".repeat(80), 0).name()).hasSize(160);
    assertThatThrownBy(() -> new Folder("😀".repeat(81), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder(" ", 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder("강의", 6)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pointRejectsNonFiniteAndOutOfRangeCoordinates() {
    assertThat(new Point(0.0, 1.0).y()).isEqualTo(1);
    for (Double x : new Double[] {null, Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01}) {
      assertThatThrownBy(() -> new Point(x, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void sourcePathMustBeOwnedAndCannotEscape() {
    assertThat(SourcePath.validate(owner, owner + "/upload/source.pptx", "자료.PPTX"))
        .endsWith(".pptx");
    for (String path :
        new String[] {
          "other/source.pdf",
          owner + "/../source.pdf",
          owner + "/%2e%2e/source.pdf",
          owner + "//source.pdf",
          owner + "/a\\source.pdf"
        }) {
      assertThatThrownBy(() -> SourcePath.validate(owner, path, "source.pdf"))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> SourcePath.validate(owner, owner + "/x.pdf", "x.pptx"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

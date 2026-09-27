package com.ohpin;

import static org.assertj.core.api.Assertions.*;

import com.ohpin.folder.dto.CreateFolderRequest;
import com.ohpin.folder.entity.Folder;
import com.ohpin.material.entity.SourcePath;
import com.ohpin.question.entity.Point;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  private final UUID owner = UUID.randomUUID();

  @Test
  void folderUsesUnicodeCodePointsAndTrims() {
    var folder = new CreateFolderRequest("  강의  ", 5, "other", "  정규 수업  ").toEntity();
    assertThat(folder.name()).isEqualTo("강의");
    assertThat(folder.purpose()).isEqualTo("other");
    assertThat(folder.purposeLabel()).isEqualTo("정규 수업");
    assertThat(new Folder("교육", 0, "education", "ignored").purposeLabel()).isNull();
    assertThat(new Folder("기타", 0, "other", " ").purposeLabel()).isNull();
    assertThat(new Folder("😀".repeat(80), 0, "qa", null).name()).hasSize(160);
    assertThatThrownBy(() -> new Folder("😀".repeat(81), 0, "qa", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder(" ", 0, "qa", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder("강의", 6, "qa", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder("강의", 0, "class", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Folder("강의", 0, "other", "가".repeat(41)))
        .isInstanceOf(IllegalArgumentException.class);
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

package com.ohpin.folder.dto;

import com.ohpin.folder.entity.Folder;
import com.ohpin.shared.domain.Rules;
import java.util.LinkedHashMap;
import java.util.Map;

public record UpdateFolderRequest(String name, String purpose, String purposeLabel) {
  public Map<String, Object> toFields() {
    var fields = new LinkedHashMap<String, Object>();
    if (name != null) fields.put("name", Rules.text(name, "Folder name", 1, 80));
    if (purpose != null) {
      String normalizedPurpose = Folder.validatePurpose(purpose);
      fields.put("purpose", normalizedPurpose);
      if (!"other".equals(normalizedPurpose)) fields.put("purpose_label", null);
      else if (purposeLabel != null)
        fields.put("purpose_label", Folder.normalizePurposeLabel(purposeLabel));
    } else if (purposeLabel != null) {
      fields.put("purpose_label", Folder.normalizePurposeLabel(purposeLabel));
    }
    Rules.check(!fields.isEmpty(), "Folder update is required");
    return fields;
  }
}

package com.ohpin.folder.dto;

import com.ohpin.folder.entity.Folder;

public record CreateFolderRequest(
        String name, Integer colorIndex, String purpose, String purposeLabel) {
    public Folder toEntity() {
        return new Folder(name, colorIndex, purpose, purposeLabel);
    }
}

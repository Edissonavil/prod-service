package com.aec.prodsrv.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileInfoResponse(
        Long id,
        @JsonProperty("driveFileId") String driveFileId,
        String filename,
        @JsonProperty("originalName") String originalName,
        String fileType,
        Long size,
        String uploader,
        String downloadUri
) {}
package com.aec.prodsrv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileDto {
    private Long id;
    private String filename;
    private String originalName;
    private String fileType;
    private long size;
    private String uploader;
    private String downloadUri;
}

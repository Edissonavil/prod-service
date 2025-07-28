package com.aec.prodsrv.client.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @ToString
public class FileInfoDto {
  private Long id;
  private String filename;
  private String originalName;
  private String fileType;
  private Long size;
  private String uploader;
  private String driveFileId;   // <- CLAVE CRÍTICA: debe llamarse exactamente así
  private String downloadUri;
}

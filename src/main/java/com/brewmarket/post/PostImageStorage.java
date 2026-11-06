package com.brewmarket.post;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface PostImageStorage {
    String save(MultipartFile image);
    boolean delete(String key);
    Path resolve(String key);
}

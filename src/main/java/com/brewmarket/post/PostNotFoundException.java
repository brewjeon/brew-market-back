package com.brewmarket.post;

// 조회한 번호에 해당하는 게시글이 없는 상황을 표현
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException() {
        super("게시글을 찾을 수 없습니다.");
    }
}

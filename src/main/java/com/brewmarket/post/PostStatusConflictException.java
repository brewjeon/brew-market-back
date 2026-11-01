package com.brewmarket.post;

public class PostStatusConflictException extends RuntimeException {
    public PostStatusConflictException() {
        super("거래 완료된 게시글의 상태는 변경할 수 없습니다.");
    }
}

package zerobase.weather.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 플젝 전반의 예외처리 .. 전역예외
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class) // 컨트롤러 안에잇는것만 예외처리
    public  Exception handlerAllException(){
        System.out.println("error from GlobalExceptionHandler !! ");
        return new Exception();
    }
}

/*
예외발생시,
서버가 동작하다가 예외 발생할때 GlobalExceptionHandler 가 그 예외를 잡아옴.
그 시점이 클라이언트 에서 서버 API 호출한 시점일때
어떤걸 반환할꺼냐 --- 그때 사용하는 어노테이션
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
INTERNAL_SERVER_ERROR : 500 으로 서버에 문제가 있다..의미

 */
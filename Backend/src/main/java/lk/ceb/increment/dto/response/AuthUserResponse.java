package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthUserResponse {
    private String username;
    private String displayName;
    private String role;
}

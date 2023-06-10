package com.sample.spring_jpa_auditing.securities;


import com.sample.spring_jpa_auditing.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.header.string}")
    public String HEADER_STRING;

    @Value("${jwt.header.apiKeyString}")
    public String API_KEY_HEADER_STRING;

    @Value("${jwt.header.userIdString}")
    public String USER_ID_HEADER_STRING;

    @Value("${jwt.header.tenantIdString}")
    public String TENANT_ID_HEADER_STRING;

    @Value("${jwt.token.prefix}")
    public String TOKEN_PREFIX;

    @Value("${jwt.apiKey}")
    public String API_KEY;

    @Autowired
    private JwtUtil jwtUtil;

    private final List<String> unauthApis = Arrays.asList(
            "/users/authenticate",
            "/users/register"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {

        var url = req.getRequestURL();

        // flag to check if this url is filtered or not, mark true to ignore url filtering
        var filtered = false;

        for (String api: unauthApis) {
            if (!filtered && url.toString().contains(api)) {
                chain.doFilter(req, res);
                filtered = true;
            }
        }

        // authenticating based on api key, for internal api calls
        if (!filtered && isApiKeyPresent(req)) {

            var apiKey = getApiKey(req);

            if (apiKey.equals(API_KEY)) {

                var userId = getUserId(req);

                if (userId != null) {

                    var tenantId = getTenantId(req);

                    setSignedUser(userId, tenantId, "");

                    chain.doFilter(req, res);
                    filtered = true;
                }
            }
        }

        if (!filtered && !this.isAuthMissing(req) && !this.isPrefixMissing(req)) {
            final String token = this.getAuthHeader(req);

            if (!jwtUtil.isInvalid(token)) {

                Claims claims = jwtUtil.getAllClaimsFromToken(token);

                var userId =  String.valueOf(claims.get("id"));
                var tenantId =  String.valueOf(claims.get("tenantId"));
                var roles =  String.valueOf(claims.get("roles"));

                setSignedUser(userId, tenantId, roles);

            }
        }

        if (!filtered) {
            chain.doFilter(req, res);
        }
    }

    public static void setSignedUser(String userId, String tenantId, String roles) {
        var authorities = List.of(new SimpleGrantedAuthority(roles != null && !roles.isEmpty() ? roles : "anonymous"));

        var userDetails = new SignedUser();

        userDetails.setUserId(Long.valueOf(userId));
        userDetails.setTenantId(tenantId);
        userDetails.setAuthorities(authorities);
        userDetails.setPassword("");
        userDetails.setUsername("");
        userDetails.setEnabled(true);
        userDetails.setAccountNonExpired(true);
        userDetails.setAccountNonLocked(true);
        userDetails.setCredentialsNonExpired(true);

        var authentication = new UsernamePasswordAuthenticationToken(userDetails, "", authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getAuthHeader(HttpServletRequest request) {
        var header = request.getHeader(HEADER_STRING);
        return header.replace(TOKEN_PREFIX,"").trim();
    }

    private boolean isAuthMissing(HttpServletRequest request) {
        return request.getHeader(HEADER_STRING) == null;
    }

    private boolean isApiKeyPresent(HttpServletRequest request) {
        return request.getHeader(API_KEY_HEADER_STRING) != null;
    }

    private String getApiKey(HttpServletRequest request) {
        return request.getHeader(API_KEY_HEADER_STRING);
    }

    private String getUserId(HttpServletRequest request) {
        return request.getHeader(USER_ID_HEADER_STRING);
    }

    private String getTenantId(HttpServletRequest request) {
        return request.getHeader(TENANT_ID_HEADER_STRING);
    }

    private boolean isPrefixMissing(HttpServletRequest request) {
        var header = request.getHeader(HEADER_STRING);
        assert header != null;
        return !header.startsWith(TOKEN_PREFIX);
    }

    private void onError(HttpServletResponse response, String err, HttpStatus httpStatus) {
        response.setStatus(httpStatus.value());
    }
}

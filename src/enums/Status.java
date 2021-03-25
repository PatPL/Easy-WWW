package enums;

public enum Status {
    
    // 1xx: Informational - Request received, continuing process
    Continue_100 (100, "Continue"),
    SwitchingProtocols_101 (101, "Switching Protocols"),
    
    // 2xx: Success - The action was successfully received, understood, and accepted
    OK_200 (200, "OK"),
    Created_201 (201, "Created"),
    Accepted_202 (202, "Accepted"),
    NonAuthorativeInformation_203 (203, "Non-Authoritative Information"),
    NoContent_204 (204, "No Content"),
    ResetContent_205 (205, "Reset Content"),
    PartialContent_206 (206, "Partial Content"),
    
    // 3xx: Redirection - Further action must be taken in order to complete the request
    MultipleChoices_300 (300, "Multiple Choices"),
    MovedPermanently_301 (301, "Moved Permanently"),
    Found_302 (302, "Found"),
    SeeOther_303 (303, "See Other"),
    NotModified_304 (304, "Not Modified"),
    UseProxy_305 (305, "Use Proxy"),
    TemporaryRedirect_307 (307, "Temporary Redirect"),
    
    // 4xx: Client Error - The request contains bad syntax or cannot be fulfilled
    BadRequest_400 (400, "Bad Request"),
    Unauthorized_401 (401, "Unauthorized"),
    PaymentRequired_402 (402, "Payment Required"),
    Forbidden_403 (403, "Forbidden"),
    NotFound_404 (404, "Not Found"),
    MethodNotAllowed_405 (405, "Method Not Allowed"),
    NotAcceptable_406 (406, "Not Acceptable"),
    ProxyAuthenticationRequired_407 (407, "Proxy Authentication Required"),
    RequestTimeout_408 (408, "Request Time-out"),
    Conflict_409 (409, "Conflict"),
    Gone_410 (410, "Gone"),
    LengthRequired_411 (411, "Length Required"),
    PreconditionFailed_412 (412, "Precondition Failed"),
    RequestEntityTooLarge_413 (413, "Request Entity Too Large"),
    RequestURITooLarge_414 (414, "Request-URI Too Large"),
    UnsupportedMediaType_415 (415, "Unsupported Media Type"),
    RequestedRangeNotSatisfiable_416 (416, "Requested range not satisfiable"),
    ExpectationFailed_417 (417, "Expectation Failed"),
    
    // 5xx: Server Error - The server failed to fulfill an apparently valid request
    InternalServerError_500 (500, "Internal Server Error"),
    NotImplemented_501 (501, "Not Implemented"),
    BadGateway_502 (502, "Bad Gateway"),
    ServiceUnavailable_503 (503, "Service Unavailable"),
    GatewayTimeout_504 (504, "Gateway Time-out"),
    HTTPVersionNotSupported_505 (505, "HTTP Version not supported");
    
    public final int code;
    public final String message;
    public final StatusType type;
    
    Status (int i, String j) {
        this.code = i;
        this.message = j;
        this.type = getStatusType (i);
        
    }
    
    public static StatusType getStatusType (int i) {
        int type = i / 100;
        if (type == 1) {
            return StatusType.Informational_1xx;
        } else if (type == 2) {
            return StatusType.Success_2xx;
        } else if (type == 3) {
            return StatusType.Redirection_3xx;
        } else if (type == 4) {
            return StatusType.ClientError_4xx;
        } else if (type == 5) {
            return StatusType.ServerError_5xx;
        } else {
            return StatusType.Other_xxx;
        }
    }
    
}
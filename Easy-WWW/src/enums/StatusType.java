package enums;

public enum StatusType {
    
    // xxx: Other - Custom status code that doesn't fit any other category
    Other_xxx,
    
    // 1xx: Informational - Request received, continuing process
    Informational_1xx,
    
    // 2xx: Success - The action was successfully received, understood, and accepted
    Success_2xx,
    
    // 3xx: Redirection - Further action must be taken in order to complete the request\
    Redirection_3xx,
    
    // 4xx: Client Error - The request contains bad syntax or cannot be fulfilled
    ClientError_4xx,
    
    // 5xx: Server Error - The server failed to fulfill an apparently valid request
    ServerError_5xx;
    
}

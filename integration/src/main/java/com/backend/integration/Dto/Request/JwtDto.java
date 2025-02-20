package com.backend.integration.Dto.Request;


public record JwtDto(
    String accessToken,
    String userId,
    String userName,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {
    // Constructor with parameters
    public JwtDto {
        // Any additional initialization logic here
    }
    public JwtDto(String accessToken) {
        this(accessToken, null, null, null, null, null, null);
    }
    // Example of a with method to create a new instance with additional fields
    public JwtDto withUserInformation(String userId, String userName, String firstName, String lastName, String email, String phoneNumber) {
        return new JwtDto(this.accessToken(), userId, userName, firstName, lastName, email, phoneNumber);
    }
}
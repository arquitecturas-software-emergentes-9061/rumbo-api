package com.uitopic.rumbo.navigation.interfaces.rest.resources;

/** HTTP error body for invalid published-directory query parameters. */
public record DirectoryErrorResource(String code, String message) {}

package com.glosh.remote.spike.session;

public enum PairingUiState {
    INACTIVE,
    CHECKING_SAVED_IDENTITY,
    DISCOVERING_ENDPOINT,
    WAITING_FOR_CODE,
    CONNECTING,
    CODE_FAILED
}

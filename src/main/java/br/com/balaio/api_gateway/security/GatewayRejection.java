package br.com.balaio.api_gateway.security;

final class GatewayRejection extends RuntimeException {
    private final int status;

    GatewayRejection(int status) {
        super(null, null, false, false);
        this.status = status;
    }

    int status() {
        return status;
    }
}

class PortError(Exception):
    """An explicit validation, policy, capability, or reconciliation blocker."""


class ApiError(PortError):
    def __init__(self, method: str, path: str, status: int):
        self.status = status
        super().__init__(f"GitHub {method} {path.split('?')[0]} returned HTTP {status}")


class ReceiptError(PortError):
    """An external mutation returned an identity but subsequent verification failed."""

    def __init__(self, message: str, identity):
        self.identity = identity
        super().__init__(message)

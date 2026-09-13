package com.umlcollab.backend.exception;

/** Se lanza cuando un usuario intenta editar un elemento bloqueado por otro (exclusion mutua). */
public class LockConflictException extends RuntimeException {
    private final String lockedByDisplayName;

    public LockConflictException(String lockedByDisplayName) {
        super("El elemento esta siendo editado por " + lockedByDisplayName);
        this.lockedByDisplayName = lockedByDisplayName;
    }

    public String getLockedByDisplayName() {
        return lockedByDisplayName;
    }
}

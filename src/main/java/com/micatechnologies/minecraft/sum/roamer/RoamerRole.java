package com.micatechnologies.minecraft.sum.roamer;

/**
 * Role tag for {@link EntityRoamer}. Drives the default greeting list and is the hook future
 * features (idle animation, location preferences, dialogue trees) can branch on. Survives
 * via NBT as a string id; unknown ids fall back to {@link #GENERIC}.
 *
 * <p>Adding a new role: append an enum constant with its id and default-greetings array. If
 * the role wires into a feature (e.g. a SUM block expecting a particular role nearby), wire
 * that on the consumer side - this enum stays a pure tag.
 */
public enum RoamerRole {

    GENERIC("generic", null, new String[]{
        "Hello!",
        "Hey there!",
        "Welcome!"
    }),

    BANK_TELLER("bank_teller", "Bank Teller", new String[]{
        "Welcome to First National.",
        "Cash or check?",
        "Have a nice day.",
        "Need help with your account?",
        "Please step up to the counter.",
        "Next in line, please."
    });

    private final String id;
    private final String defaultName;
    private final String[] defaultGreetings;

    RoamerRole(String id, String defaultName, String[] defaultGreetings) {
        this.id = id;
        this.defaultName = defaultName;
        this.defaultGreetings = defaultGreetings;
    }

    public String getId() {
        return id;
    }

    /** Display name auto-applied on role assignment when the roamer has no custom name yet.
     *  Null means "leave the name alone" (used by GENERIC). */
    public String getDefaultName() {
        return defaultName;
    }

    public String[] getDefaultGreetings() {
        return defaultGreetings.clone();
    }

    /** Returns the role with the given id, or {@link #GENERIC} for unknown / null input. */
    public static RoamerRole fromId(String id) {
        if (id == null) {
            return GENERIC;
        }
        for (RoamerRole role : values()) {
            if (role.id.equalsIgnoreCase(id)) {
                return role;
            }
        }
        return GENERIC;
    }
}

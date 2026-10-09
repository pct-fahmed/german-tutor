package tutor.ai;

public enum Scenario {

    FREE("Freies Gespräch", null),
    BAKERY("Beim Bäcker",
            "You are a baker in a small German Bäckerei. The learner is a customer buying bread and pastries."),
    DOCTOR("Beim Arzt",
            "You are the receptionist and then the doctor at a Hausarztpraxis. The learner is a patient who feels unwell."),
    RESTAURANT("Im Restaurant",
            "You are a waiter in a German restaurant. The learner wants to order food and drinks and later pay."),
    DIRECTIONS("Nach dem Weg fragen",
            "You are a friendly passer-by in a German city. The learner asks for directions to the train station."),
    APARTMENT("Wohnungssuche",
            "You are a landlord showing a small apartment. The learner is interested in renting it."),
    JOB_INTERVIEW("Vorstellungsgespräch",
            "You are an interviewer at a German company. The learner is applying for a job in their own field.");

    private final String label;
    private final String rolePlay;

    Scenario(String label, String rolePlay) {
        this.label = label;
        this.rolePlay = rolePlay;
    }

    public String rolePlay() {
        return rolePlay;
    }

    public boolean isRolePlay() {
        return rolePlay != null;
    }

    @Override
    public String toString() {
        return label;
    }
}

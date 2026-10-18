package com.squareetlabs.ticketbai.model;

/** Listas de códigos del XSD TicketBAI 1.2.2. Cada constante lleva el código tal como va en el fichero. */
public final class Codes {

    private Codes() {
    }

    /** Código con el valor que se escribe en el XML. */
    public interface Coded {
        String code();
    }

    /** {@code ClaveRegimenIvaOpTrascendencia}. */
    public enum RegimeKey implements Coded {
        GENERAL("01"), EXPORT("02"), USED_GOODS("03"), INVESTMENT_GOLD("04"), TRAVEL_AGENCIES("05"), VAT_GROUP("06"),
        CASH_CRITERION("07"), IPSI_IGIC("08"), TRAVEL_AGENCY_MEDIATION("09"), THIRD_PARTY_COLLECTIONS("10"),
        BUSINESS_PREMISES_RENTAL_WITH_WITHHOLDING("11"), BUSINESS_PREMISES_RENTAL_WITHOUT_WITHHOLDING("12"),
        BUSINESS_PREMISES_RENTAL_MIXED("13"),
        /** IVA pendiente de devengo en certificaciones de obra cuyo destinatario es una Administración Pública. */
        PUBLIC_WORKS_CERTIFICATION("14"),
        SUCCESSIVE_TRACT("15"), OSS_IOSS("17"), REAGYP("19"), EQUIVALENCE_SURCHARGE("51"), SIMPLIFIED_REGIME("52"),
        NON_BUSINESS("53"), PERMANENT_ESTABLISHMENT_CANARIAS_CEUTA_MELILLA("54");

        private final String code;

        RegimeKey(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    /** {@code CausaExencion}: artículos de la normativa del IVA. */
    public enum ExemptionCause implements Coded {
        E1, E2, E3, E4, E5, E6;

        @Override
        public String code() {
            return name();
        }
    }

    /** {@code TipoNoExenta}: S1 sin inversión del sujeto pasivo, S2 con inversión. */
    public enum NonExemptType implements Coded {
        S1, S2;

        @Override
        public String code() {
            return name();
        }
    }

    /** {@code CausaNoSujeta}: OT (art. 7 y otros), RL (reglas de localización), VT (por cuenta de terceros), IE. */
    public enum NotSubjectCause implements Coded {
        OT, RL, VT, IE;

        @Override
        public String code() {
            return name();
        }
    }

    /** {@code FacturaRectificativa/Codigo}. R5 es la rectificativa de una simplificada. */
    public enum RectificationCode implements Coded {
        R1, R2, R3, R4, R5;

        @Override
        public String code() {
            return name();
        }
    }

    /** {@code FacturaRectificativa/Tipo}: S por sustitución, I por diferencias. */
    public enum RectificationType implements Coded {
        SUBSTITUTION("S"), DIFFERENCES("I");

        private final String code;

        RectificationType(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    /** {@code IDOtro/IDType}. TicketBAI no tiene el 07 (no censado) de VERI*FACTU. */
    public enum OtherIdType implements Coded {
        VAT_NUMBER("02"), PASSPORT("03"), OFFICIAL_ID("04"), RESIDENCE_CERTIFICATE("05"), OTHER_DOCUMENT("06");

        private final String code;

        OtherIdType(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    /** {@code EmitidaPorTercerosODestinatario}. */
    public enum IssuedBy implements Coded {
        ISSUER("N"), THIRD_PARTY("T"), RECIPIENT("D");

        private final String code;

        IssuedBy(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }
}

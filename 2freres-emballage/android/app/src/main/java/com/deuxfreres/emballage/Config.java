package com.deuxfreres.emballage;

/** ⚙️ Seul fichier à modifier avant de compiler. */
public final class Config {
    /** URL du Web App Google Apps Script (se termine par /exec) */
    public static final String API_URL = "https://script.google.com/macros/s/AKfycbwwFnX10GIN42tBrLokBEaB6dJi_-V60w-sBaX8WaZ6NundpcS0EK45cokWE8RfA8fxYA/exec";
    /** Numéro WhatsApp au format international, sans + ni 0 : ex 213555123456 */
    public static final String WHATSAPP_NUMBER = "213555053539";
    /** Numéro pour le bouton "Appeler" */
    public static final String PHONE_NUMBER = "+213555053539";
    /** API des comptes et avis (Cloudflare Worker + D1) */
    public static final String API_BASE = "https://flat-violet-af44.yacincianai.workers.dev";
    public static final String STORE_NAME = "2 Frères Emballage";
    public static final String CURRENCY = "DA";

    private Config() {}
}

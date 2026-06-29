package com.quju.registration.dto;

import java.util.Map;

public class RegisterActivityRequest {

    private Map<String, String> formData;

    public Map<String, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, String> formData) {
        this.formData = formData;
    }
}

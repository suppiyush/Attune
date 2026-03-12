package com.cce.attune.context;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class UserProfileStore {
    private static final String PREFS = "user_profile_prefs";
    private static final String KEY_NAME = "name";
    private static final String KEY_AGE = "age";
    private static final String KEY_GENDER = "gender";

    private final SharedPreferences prefs;

    public UserProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isComplete() {
        String name = getName();
        int age = getAge();
        String gender = getGender();
        return name != null && !name.trim().isEmpty() && age > 0 && gender != null && !gender.trim().isEmpty();
    }

    public void save(String name, int age, String gender) {
        prefs.edit()
                .putString(KEY_NAME, name)
                .putInt(KEY_AGE, age)
                .putString(KEY_GENDER, gender)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    @Nullable
    public String getName() {
        return prefs.getString(KEY_NAME, null);
    }

    public int getAge() {
        return prefs.getInt(KEY_AGE, 0);
    }

    @Nullable
    public String getGender() {
        return prefs.getString(KEY_GENDER, null);
    }
}


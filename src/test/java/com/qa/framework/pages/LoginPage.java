package com.qa.framework.pages;

import com.microsoft.playwright.Page;
import com.qa.framework.config.ConfigReader;

public class LoginPage extends BasePage {

    private static final String USERNAME = "#user-name";
    private static final String PASSWORD = "#password";
    private static final String LOGIN_BUTTON = "#login-button";
    private static final String ERROR_MESSAGE = "[data-test='error']";

    public LoginPage(Page page) {
        super(page);
    }

    public LoginPage open() {
        page.navigate(ConfigReader.get("base.url"));
        waitForVisible(LOGIN_BUTTON);
        return this;
    }

    public void login(String username, String password) {
        type(USERNAME, username);
        type(PASSWORD, password);
        click(LOGIN_BUTTON);
    }

    public String getErrorMessage() {
        waitForVisible(ERROR_MESSAGE);
        return getText(ERROR_MESSAGE);
    }

    public boolean isLoginButtonVisible() {
        return isVisible(LOGIN_BUTTON);
    }
}

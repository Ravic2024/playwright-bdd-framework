package com.qa.framework.pages;

import com.microsoft.playwright.Page;

/** Creates page objects lazily, once per scenario, all bound to the scenario's own Page. */
public class PageObjectManager {

    private final Page page;
    private LoginPage loginPage;
    private ProductsPage productsPage;

    public PageObjectManager(Page page) {
        this.page = page;
    }

    public LoginPage loginPage() {
        if (loginPage == null) {
            loginPage = new LoginPage(page);
        }
        return loginPage;
    }

    public ProductsPage productsPage() {
        if (productsPage == null) {
            productsPage = new ProductsPage(page);
        }
        return productsPage;
    }
}

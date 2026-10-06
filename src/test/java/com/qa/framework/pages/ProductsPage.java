package com.qa.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class ProductsPage extends BasePage {

    private static final String PAGE_HEADER = "[data-test='title']";
    private static final String ADD_TO_CART_BUTTONS = "button[data-test^='add-to-cart']";
    private static final String CART_BADGE = "[data-test='shopping-cart-badge']";
    private static final String PRODUCT_ITEMS = "[data-test='inventory-item']";

    public ProductsPage(Page page) {
        super(page);
    }

    public String getHeader() {
        waitForVisible(PAGE_HEADER);
        return getText(PAGE_HEADER);
    }

    public int getProductCount() {
        waitForVisible(PRODUCT_ITEMS);
        return page.locator(PRODUCT_ITEMS).count();
    }

    public void addProductsToCart(int count) {
        Locator buttons = page.locator(ADD_TO_CART_BUTTONS);
        for (int i = 0; i < count; i++) {
            // After each click the clicked button becomes "Remove", so always take the first one
            buttons.first().click();
        }
    }

    public String getCartBadgeCount() {
        return isVisible(CART_BADGE) ? getText(CART_BADGE) : "0";
    }
}

package com.qa.framework.stepdefinitions;

import com.qa.framework.context.TestContext;
import com.qa.framework.pages.ProductsPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

public class ProductSteps {

    private final TestContext testContext;

    public ProductSteps(TestContext testContext) {
        this.testContext = testContext;
    }

    private ProductsPage productsPage() {
        return testContext.getPages().productsPage();
    }

    @Then("the user should see {int} products listed")
    public void theUserShouldSeeProducts(int expected) {
        Assert.assertEquals(productsPage().getProductCount(), expected, "Product count mismatch");
    }

    @When("the user adds {int} product(s) to the cart")
    public void theUserAddsProductsToCart(int count) {
        productsPage().addProductsToCart(count);
    }

    @Then("the cart badge should show {string}")
    public void theCartBadgeShouldShow(String expected) {
        Assert.assertEquals(productsPage().getCartBadgeCount(), expected, "Cart badge count mismatch");
    }
}

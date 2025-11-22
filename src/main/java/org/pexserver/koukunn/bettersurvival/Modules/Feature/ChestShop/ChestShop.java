package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

public class ChestShop {

    private String owner;
    private String ownerName;
    private String name;
    private String currency; // Material name for currency item (nullable)
    private String currencyName; // human-friendly display name (nullable)
    private int earnings; // accumulated earnings from sales

    public ChestShop() {}

    public ChestShop(String owner, String ownerName, String name) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.currency = null;
        this.currencyName = null;
        this.earnings = 0;
    }

    public ChestShop(String owner, String ownerName, String name, String currency) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.currency = currency;
        this.currencyName = null;
        this.earnings = 0;
    }

    public ChestShop(String owner, String ownerName, String name, String currency, String currencyName) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.name = name;
        this.currency = currency;
        this.currencyName = currencyName;
        this.earnings = 0;
    }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }

    public int getEarnings() { return earnings; }
    public void setEarnings(int earnings) { this.earnings = earnings; }

}

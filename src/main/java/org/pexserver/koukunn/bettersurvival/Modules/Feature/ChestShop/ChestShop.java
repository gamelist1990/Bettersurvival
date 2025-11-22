package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

public class ChestShop {

    private String owner;
    private String name;
    private String currency; // Material name for currency item (nullable)

    public ChestShop() {}

    public ChestShop(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.currency = null;
    }

    public ChestShop(String owner, String name, String currency) {
        this.owner = owner;
        this.name = name;
        this.currency = currency;
    }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

}

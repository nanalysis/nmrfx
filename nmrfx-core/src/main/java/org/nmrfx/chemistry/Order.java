/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry;

/**
 * @author Bruce Johnson
 */
public enum Order {
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3),
    QUAD(4);

    int order;

    Order(int order) {
        this.order = order;

    }

    public int getOrderNum() {
        return order;
    }

    public static Order getOrder(int num) {
        Order order;
        switch (num) {
            case 1:
                order = SINGLE;
                break;
            case 2:
                order = DOUBLE;
                break;
            case 3:
                order = TRIPLE;
                break;
            case 4:
                order = QUAD;
                break;
            default:
                order = SINGLE;
        }
        return order;
    }
}

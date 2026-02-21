package com.quantitymeasurement;

public class QuantityMeasurementApp {

    public static void main(String[] args) {

        QuantityLength q1 = new QuantityLength(1.0, LengthUnit.FEET);
        QuantityLength q2 = new QuantityLength(12.0, LengthUnit.INCH);

        System.out.println("Comparing: " + q1 + " and " + q2);

        System.out.println("Equal: " + q1.equals(q2));

        QuantityLength q3 = new QuantityLength(1.0, LengthUnit.INCH);
        QuantityLength q4 = new QuantityLength(1.0, LengthUnit.INCH);

        System.out.println("Comparing: " + q3 + " and " + q4);
        System.out.println("Equal: " + q3.equals(q4));
    }
}

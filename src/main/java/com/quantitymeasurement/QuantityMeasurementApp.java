package com.quantitymeasurement;

import java.util.Scanner;

public class QuantityMeasurementApp {

	public static void main(String[] args) {
		Scanner sc=new Scanner(System.in);
		System.out.println("Enter First Number : ");
		double inputOne = sc.nextDouble();
		System.out.println("Enter Second Number : ");
		double inputTwo = sc.nextDouble();
		Feet f1 = new Feet(inputOne);
        Feet f2 = new Feet(inputTwo);

        System.out.println("Are equal? " + f1.equals(f2));
	}
}
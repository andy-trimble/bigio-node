/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.a2i.bigio.examples.cli;

import com.a2i.bigio.CommandLine;
import com.a2i.bigio.Component;

/**
 *
 * @author atrimble
 */
@Component
public class ExampleCommand implements CommandLine {

    @Override
    public String getCommand() {
        return "hello";
    }

    @Override
    public void execute(String... args) {
        System.out.println(" world");
    }

    @Override
    public String help() {
        return "Hello World!";
    }
}
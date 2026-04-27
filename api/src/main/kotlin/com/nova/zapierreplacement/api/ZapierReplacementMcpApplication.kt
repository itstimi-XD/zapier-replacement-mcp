package com.nova.zapierreplacement.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.nova.zapierreplacement"])
class ZapierReplacementMcpApplication

fun main(args: Array<String>) {
    runApplication<ZapierReplacementMcpApplication>(*args)
}

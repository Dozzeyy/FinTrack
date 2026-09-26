/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 */

package com.openapps.fintrack.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object DohNetworkClient {

    private val bootstrapClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val dnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            //.url("https://dns.google/dns-query".toHttpUrl())
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                //InetAddress.getByName("8.8.8.8"),
                //InetAddress.getByName("8.8.4.4"),
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("1.1.1.2"),
                InetAddress.getByName("1.0.0.2")
            )
            .build()
    }

    fun createClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .dns(dnsOverHttps)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.scheme.equals("http", ignoreCase = true)) {
                    val httpsUrl = request.url.newBuilder().scheme("https").build()
                    val httpsRequest = request.newBuilder().url(httpsUrl).build()
                    chain.proceed(httpsRequest)
                } else {
                    chain.proceed(request)
                }
            }
    }
}

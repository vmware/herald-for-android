//  Copyright 2020-2021 Herald Project Contributors
//  SPDX-License-Identifier: Apache-2.0
//

package io.heraldprox.herald.sensor.payload.simple;

import io.heraldprox.herald.sensor.data.ConcreteSensorLogger;
import io.heraldprox.herald.sensor.data.SensorLogger;
import io.heraldprox.herald.sensor.datatype.TimeInterval;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

/// Key generation functions
public class K {
    /// Secret key length
    private final static int secretKeyLength = 2048;
    /// Days supported by key derivation function
    private final static int days = 2000;
    /// Periods per day
    private final static int periods = 240;
    /// Epoch as time interval since 1970
    private final static TimeInterval epoch = K.getEpoch();

    /// Date from string date "yyyy-MM-dd'T'HH:mm:ssXXXX"
    protected static Date date(String fromString) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        try {
            return format.parse(fromString);
        } catch (Throwable e) {
            return null;
        }
    }

    /// Epoch for calculating days and periods
    protected static TimeInterval getEpoch() {
        final Date date = date("2020-09-24T00:00:00+0000");
        return new TimeInterval(date.getTime() / 1000);
    }

    /// Epoch day for selecting matching key
    protected static int day(Date onDate) {
        return (int) ((new TimeInterval(onDate).value - epoch.value) / 86400);
    }

    /// Epoch day period for selecting contact key
    protected static int period(Date atTime) {
        final int second = (int) ((new TimeInterval(atTime).value - epoch.value) % 86400);
        return second / (86400 / periods);
    }

    /// Generate 2048-bit secret key, K_s
    protected static SecretKey secretKey() {
        final SecureRandom secureRandom = getSecureRandom();
        final byte[] bytes = new byte[secretKeyLength];
        secureRandom.nextBytes(bytes);
        return new SecretKey(bytes);
    }

    public final static SecureRandom getSecureRandom() {
        final SensorLogger logger = new ConcreteSensorLogger("Sensor", "Payload.Simple.K");
        try {
            // Retrieve a SHA1PRNG
            final SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            // Generate a secure seed
            final SecureRandom seedSr = new SecureRandom();
            // We need a 440 bit seed - see NIST SP800-90A
            final byte[] seed = seedSr.generateSeed(55);
            sr.setSeed(seed); // seed with random number
            // Securely generate bytes
            sr.nextBytes(new byte[256 + sr.nextInt(1024)]); // start from random position
            return sr;
        } catch (Throwable e) {
            logger.fault("Could not retrieve SHA1PRNG SecureRandom instance", e);
            return new SecureRandom();
        }
    }

    /// Generate matching keys K_{m}^{0...days}
    protected static MatchingKey[] matchingKeys(SecretKey secretKey) {
        final int n = days;
        /**
         Forward secured matching key seeds are generated by a reversed hash chain with truncation, to ensure future keys cannot be derived from historic keys. The cryptographic hash function offers a one-way function for forward security. The truncation function offers additional assurance by deleting intermediate key material, thus a compromised hash function will still maintain forward security.
         */
        final MatchingKeySeed[] matchingKeySeed = new MatchingKeySeed[n + 1];
        /**
         The last matching key seed on day 2000 (over 5 years from epoch) is the hash of the secret key. A new secret key will need to be established before all matching key seeds are exhausted on day 2000.
         */
        matchingKeySeed[n] = new MatchingKeySeed(F.hash(secretKey));
        for (int i=n; i-->0;) {
            matchingKeySeed[i] = new MatchingKeySeed(F.hash(F.truncate(matchingKeySeed[i + 1])));
        }
        /**
         Matching key for day i is the hash of the matching key seed for day i xor i - 1. A separation of matching key from its seed is necessary because the matching key is distributed by the server to all phones for on-device matching in a decentralised contact tracing solution. Given a seed is used to derive the seeds for other days, publishing the hash prevents an attacker from establishing the other seeds.
         */
        final MatchingKey[] matchingKey = new MatchingKey[n + 1];
        for (int i=1; i<=n; i++) {
            matchingKey[i] = new MatchingKey(F.hash(F.xor(matchingKeySeed[i], matchingKeySeed[i - 1])));
        }
        /**
         Matching key on day 0 is derived from matching key seed on day 0 and day -1. Implemented as special case for clarity in above code.
         */
        final MatchingKeySeed matchingKeySeedMinusOne = new MatchingKeySeed(F.hash(F.truncate(matchingKeySeed[0])));
        matchingKey[0] = new MatchingKey(F.hash(F.xor(matchingKeySeed[0], matchingKeySeedMinusOne)));
        return matchingKey;
    }

    /// Generate contact keys K_{c}^{0...periods}
    protected static ContactKey[] contactKeys(MatchingKey matchingKey) {
        final int n = periods;

        /**
         Forward secured contact key seeds are generated by a reversed hash chain with truncation, to ensure future keys cannot be derived from historic keys. This is identical to the procedure for generating the matching key seeds. The seeds are never transmitted from the phone. They are cryptographically challenging to reveal from the broadcasted contact keys, while easy to generate given the matching key, or secret key.
         */
        final ContactKeySeed[] contactKeySeed = new ContactKeySeed[n + 1];
        /**
         The last contact key seed on day i at period 240 (last 6 minutes of the day) is the hash of the matching key for day i.
         */
        contactKeySeed[n] = new ContactKeySeed(F.hash(matchingKey));
        for (int j=n; j-->0;) {
            contactKeySeed[j] = new ContactKeySeed(F.hash(F.truncate(contactKeySeed[j + 1])));
        }
        /**
         Contact key for day i at period j is the hash of the contact key seed for day i at period j xor j - 1. A separation of contact key from its seed is necessary because the contact key is distributed to other phones as evidence for encounters on day i within period j. Given a seed is used to derive the seeds for other periods on the same day, transmitting the hash prevents an attacker from establishing the other seeds on day i.
         */
        final ContactKey[] contactKey = new ContactKey[n + 1];
        for (int j=1; j<=n; j++) {
            contactKey[j] = new ContactKey(F.hash(F.xor(contactKeySeed[j], contactKeySeed[j - 1])));
        }
        /**
         Contact key on day 0 is derived from contact key seed at period 0 and period -1. Implemented as special case for clarity in above code.
         */
        final ContactKeySeed contactKeySeedMinusOne = new ContactKeySeed(F.hash(F.truncate(contactKeySeed[0])));
        contactKey[0] = new ContactKey(F.hash(F.xor(contactKeySeed[0], contactKeySeedMinusOne)));
        return contactKey;
    }

    /// Generate contact identifer I_{c}
    protected static ContactIdentifier contactIdentifier(ContactKey contactKey) {
        return new ContactIdentifier(F.truncate(contactKey, 16));
    }
}

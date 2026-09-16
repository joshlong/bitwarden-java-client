/**
 * A small client for the <a href="https://bitwarden.com/help/cli/">Bitwarden CLI</a>,
 * plus the Spring Boot autoconfiguration that wires it up.
 * <p>
 * {@link com.joshlong.bitwarden.Bitwarden} is the entry point: it shells out to an
 * already-unlocked {@code bw} and hands back the vault entry as JSON, which you then pick
 * apart with JsonPath. Set {@code bw.session} to a valid {@code BW_SESSION} token and the
 * autoconfiguration contributes a ready-to-use bean.
 *
 * @author Josh Long
 */
package com.joshlong.bitwarden;

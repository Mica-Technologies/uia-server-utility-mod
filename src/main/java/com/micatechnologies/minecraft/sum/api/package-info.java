/**
 * SUM's public economy API — the supported way for another mod to move a player's money.
 *
 * <p>Start at {@link com.micatechnologies.minecraft.sum.api.SumEconomy}. The reference
 * documentation, including a worked example, is in {@code docs/SUM_ECONOMY_API.md}.
 *
 * <p><b>Not to be confused with the Open MCEconomic API</b> ({@code docs/OPEN_MCECONOMIC_API_SPECIFICATION.md}),
 * which is the HTTP protocol SUM speaks to an <i>external</i> economy service. That one is about
 * where SUM keeps money; this one is about letting other mods spend it. A server may run either,
 * both, or neither — this API behaves identically whichever is the case.
 *
 * <h2>Stability contract</h2>
 *
 * <p>Everything in this package is API. It is shipped as a separate {@code -api} jar, and that jar
 * is the definition: <b>if a class is not in it, it is not API</b>, no matter how public it looks.
 * In particular {@code com.micatechnologies.minecraft.sum.economy}, {@code ...bank} and
 * {@code ...omceapi} are internals that change without notice, and code reaching into them will
 * break.
 *
 * <p>Within this package:
 *
 * <ul>
 *   <li>Existing method signatures and enum constants will not be removed or changed in meaning
 *       without bumping {@link com.micatechnologies.minecraft.sum.api.SumEconomy#API_VERSION}.</li>
 *   <li>New methods, new enum constants and new failure reasons may be added without a bump, so
 *       handle unknown {@link com.micatechnologies.minecraft.sum.api.EconomyFailure} values
 *       gracefully rather than exhaustively switching over them.</li>
 *   <li>Interfaces here are implemented by SUM and are not designed to be implemented by consumers;
 *       new methods may be added to them.</li>
 * </ul>
 *
 * <h2>Ground rules</h2>
 *
 * <ul>
 *   <li>Server-side only. Every mutation must run on the server thread.</li>
 *   <li>A failed result means nothing moved.</li>
 *   <li>The wallet is what purchases spend; the bank is savings behind an ATM. Confusing them is
 *       the most common way to write a broken integration.</li>
 * </ul>
 */
package com.micatechnologies.minecraft.sum.api;

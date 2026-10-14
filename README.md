# FX-991 Calc

An open-source Android scientific calculator that follows the Casio **fx-991ES PLUS** workflow
(SHIFT / ALPHA / MODE / SETUP, natural display, REPLAY pad). Built with Kotlin and Jetpack Compose,
tuned for tablets (landscape and portrait) as well as phones. Licensed under MIT.

> Not affiliated with or endorsed by Casio.

<!-- Screenshots: add images to docs/ and reference them here -->
*Screenshots: coming soon.*

## Download and install

1. Open the [**Releases**](../../releases) page.
2. Download the APK: `fx991-calc-<version>.apk` from a tagged release, or `fx991-calc-latest.apk` from the rolling **latest** pre-release (rebuilt on every push to `main`).
3. Copy it to your tablet (or download it directly there) and open it. When Android asks, allow
   **Install unknown apps** for your browser / file manager (Settings → Apps → Special access → Install unknown apps).
4. Release APKs are signed, so they install normally.

### Signing secrets (so updates install over previous versions)

Android only lets an APK update an installed app if both are signed with the same key. Add these
repository secrets (Settings → Secrets and variables → Actions):

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | your keystore file, base64 encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create a keystore once with
`keytool -genkeypair -v -keystore release.jks -alias fx991 -keyalg RSA -keysize 2048 -validity 10000`.
If the secrets are missing, CI generates a **temporary keystore on the fly**: the APK is still signed and
installable, but every build uses a different key, so you must uninstall the previous version before installing a new one.

## CI / releases

- `build.yml` — on push / pull request: unit tests, debug APK artifact. On push to `main` it also refreshes the rolling `latest` pre-release.
- `release.yml` — on a `v*` tag (or manual *Run workflow* with a version input, which creates the tag): tests, signed release APK, GitHub Release with generated notes.

## Build locally

Requires JDK 17 and the Android SDK (platform 34).

```bash
./gradlew testDebugUnitTest   # unit tests (engine + calculator model, no Android needed)
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

## Architecture

- `engine/` — pure Kotlin math engine (no Android dependencies): tokenizer/parser, exact rational arithmetic, radicals, complex numbers, matrices, polynomials, statistics, base-N, calculus, formatting.
- `model/` — pure Kotlin calculator state machine (`CalculatorModel`): keys, SHIFT/ALPHA, menus, grids, history, persistence.
- `ui/` — Compose UI: natural display, keypad, menus, help, settings.
- Root package — `MainActivity` and a ViewModel exposing a `StateFlow`; state is persisted in SharedPreferences.

## Features by mode

- **COMP** — Casio-like precedence, implicit multiplication, `(-)`, `x²`, `x⁻¹`, `!`, `%`, `×10ˣ`; exact fractions, mixed fractions, simplified radicals, multiples of π, S⇔D; Deg/Rad/Gra; trig, inverse, hyperbolic; log, ln, logₐ, powers and roots; Abs, Int, Intg, Rnd, GCD, LCM, nPr, nCr, Pol/Rec, Ran#, RanInt#; ∫dx, d/dx, Σ; CALC and SOLVE; 40 CONST and 40 CONV; DMS entry/conversion; ENG notation; Norm1/Norm2/Fix/Sci; variables A–F, X, Y, M, Ans, PreAns.
- **CMPLX** — arithmetic, powers, roots, Abs, Arg, Conjg, ReP, ImP, ▶r∠θ, ▶a+bi, complex exp/ln/trig/sqrt.
- **EQN** — 2 and 3 unknown linear systems; polynomials of degree 2, 3 (and 4 as an extension) with exact real and complex roots where possible; paging through x1, x2, …
- **MATRIX** — MatA/B/C/Ans up to 4×4; +, −, ×, scalar, Det, Trn, inverse, Abs, Identity, Ran#, x², x³, integer powers; exact rational arithmetic.
- **VECTOR** — VctA/B/C in 2D/3D; +, −, scalar, Dot, Cross, Abs, Angle, UnitV.
- **STAT** — 1-VAR and regressions (A+BX, _+CX², ln X, e^X, A·B^X, A·X^B, 1/X), optional FREQ column; n, x̄, Σx, Σx², σx, sx, min/max, quartiles, a, b, c, r, x̂, ŷ; normal distribution.
- **BASE-N** — DEC/HEX/BIN/OCT, and/or/xor/xnor/Not/Neg, 32-bit signed.
- **TABLE** — f(x) and optional g(x), Start/End/Step, up to 30 rows.
- **Errors** — Math / Syntax / Stack / Argument ERROR; after an error ◀/▶ moves the cursor to the error position.

## Key reference

Yellow legends are used after **SHIFT**, red legends after **ALPHA**. Full list in the in-app **Help** screen (SHIFT + 6).

| Key | SHIFT | ALPHA |
| --- | --- | --- |
| MODE | SETUP | |
| CALC | SOLVE | = |
| ∫dx | d/dx | |
| x⁻¹ | x! | |
| logₐ▫ | Σ | |
| ▫/▫ | a b/c | |
| √ | ∛ | |
| x² | x³ | |
| ^ | ˣ√ | |
| log / ln | 10ˣ / eˣ | |
| (−) | ∠ | A |
| °′″ | ▶DMS | B |
| hyp | | C |
| sin / cos / tan | sin⁻¹ / cos⁻¹ / tan⁻¹ | D / E / F |
| RCL | STO | |
| ENG | ← (ENG back) | |
| ( | Abs | Y |
| ) | , | X |
| S⇔D | a b/c ⇔ d/c | |
| M+ | M− | M |
| 7 / 8 / 9 | CONST / CONV / CLR | |
| 4 / 5 / 6 | MATRIX / VECTOR / HELP | |
| 1 / 2 / 3 | STAT / CMPLX / BASE | |
| 0 / . | Ran# / RanInt | |
| ×10ˣ | π | e |
| Ans | DRG▶ | PreAns |
| × / ÷ | nPr / nCr | |
| + / − | Pol( / Rec( | |
| DEL | INS (insert/overwrite) | |

Hardware keyboard: digits, `+ - * / ^ ( )`, Enter or `=` → equals, Backspace → DEL, Esc → AC, arrow keys → REPLAY pad.

## Roadmap and known differences

- Layout is inspired by, not pixel-identical to, the real device; key legends follow the fx-991ES PLUS.
- Matrices up to 4×4 and degree-4 polynomials are extensions beyond the real calculator.
- Calculation results use double precision (15 digits internally) with exact forms for rationals, radicals and π multiples.
- Not yet implemented: complex matrix entries, a few rarely used fx-991ES PLUS statistics options.
- The Android UI could not be compiled in the development sandbox used to write the first version; check the CI run.

## License

[MIT](LICENSE)

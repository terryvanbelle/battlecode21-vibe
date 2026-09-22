# Which metric starts predicting the result first

47 games, 40 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. **Onset** is the first round where the correlation reaches *+*threshold and holds.
**Anti** is the first round where it reaches *-*threshold and holds: there the metric predicts the result
backwards, which means either the orientation is wrong or something counter-intuitive is happening early.
A metric with an early anti and a late onset is changing sign, not rising early.
See `progress/METRICS.md` for how each quantity is computed.

| onset | anti | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|---|
| r50 | - | muc (us-them) | +0.59 | +0.33 | +0.35 | +0.38 | +0.37 | +0.37 | +0.48 |
| r50 | - | muc (us-them) ~avg | +0.47 | +0.32 | +0.34 | +0.35 | +0.37 | +0.36 | +0.41 |
| r100 | - | cov (us-them) | +0.65 | +0.07 | +0.31 | +0.35 | +0.44 | +0.47 | +0.59 |
| r150 | - | cov (us-them) ~avg | +0.54 | +0.06 | +0.27 | +0.35 | +0.41 | +0.41 | +0.51 |
| r200 | - | navMoves (us-them) | +0.58 | -0.01 | +0.17 | +0.33 | +0.38 | +0.45 | +0.53 |
| r200 | - | navMoves (us-them) ~avg | +0.54 | +0.01 | +0.16 | +0.33 | +0.39 | +0.43 | +0.50 |
| r250 | - | ec (us-them) | +0.72 | . | +0.20 | +0.11 | +0.56 | +0.55 | +0.63 |
| r250 | - | ec (us-them) ~avg | +0.61 | . | +0.20 | +0.16 | +0.45 | +0.55 | +0.56 |
| r250 | - | ecGain (us-them) | +0.59 | . | +0.20 | +0.06 | +0.44 | +0.47 | +0.52 |
| r250 | - | ecLoss (us-them) [inverted] | +0.71 | . | +0.07 | +0.19 | +0.60 | +0.54 | +0.62 |
| r250 | - | ecLoss (us-them) [inverted] ~avg | +0.64 | . | +0.07 | +0.12 | +0.48 | +0.57 | +0.59 |
| r250 | - | pol (us-them) | +0.74 | +0.01 | -0.13 | +0.11 | +0.47 | +0.59 | +0.67 |
| r250 | - | sla (us-them) | +0.64 | -0.16 | -0.11 | +0.14 | +0.51 | +0.58 | +0.57 |
| r300 | - | ecGain (us-them) ~avg | +0.49 | . | +0.20 | +0.14 | +0.35 | +0.44 | +0.44 |
| r300 | - | ecInf (us-them) | +0.52 | -0.13 | -0.03 | +0.11 | +0.36 | +0.45 | +0.46 |
| r300 | - | exp (us-them) | +0.38 | +0.11 | +0.17 | +0.12 | +0.38 | +0.31 | +0.27 |
| r300 | - | exp (us-them) ~avg | +0.38 | +0.11 | +0.22 | +0.25 | +0.35 | +0.33 | +0.27 |
| r300 | - | pol (us-them) ~avg | +0.66 | -0.00 | -0.07 | +0.05 | +0.33 | +0.50 | +0.61 |
| r300 | - | sla (us-them) ~avg | +0.62 | -0.14 | -0.12 | +0.02 | +0.32 | +0.49 | +0.57 |
| r350 | - | unitInf (us-them) | +0.70 | -0.07 | -0.01 | -0.08 | +0.18 | +0.56 | +0.65 |
| r400 | - | ecInf (us-them) ~avg | +0.54 | -0.13 | -0.04 | +0.10 | +0.21 | +0.37 | +0.50 |
| r400 | - | unitInf (us-them) ~avg | +0.69 | -0.06 | -0.01 | -0.06 | +0.06 | +0.31 | +0.65 |
| - | - | buff (us-them) | +0.28 | +0.12 | +0.13 | +0.05 | +0.27 | +0.07 | +0.07 |
| - | - | buff (us-them) ~avg | +0.32 | +0.12 | +0.17 | +0.18 | +0.26 | +0.20 | +0.20 |
| - | r400 | navAba (us-them) [inverted] | -0.50 | +0.10 | +0.14 | -0.06 | -0.21 | -0.32 | -0.45 |
| - | r500 | navAba (us-them) [inverted] ~avg | -0.44 | +0.12 | +0.14 | -0.02 | -0.14 | -0.23 | -0.38 |
| - | - | navSwamp (us-them) [inverted] | . | . | . | . | . | . | . |
| - | - | navSwamp (us-them) [inverted] ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

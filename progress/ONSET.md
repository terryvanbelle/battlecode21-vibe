# Which metric starts predicting the result first

47 games, 19 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. **Onset** is the first round where the correlation reaches *+*threshold and holds.
**Anti** is the first round where it reaches *-*threshold and holds: there the metric predicts the result
backwards, which means either the orientation is wrong or something counter-intuitive is happening early.
A metric with an early anti and a late onset is changing sign, not rising early.
See `progress/METRICS.md` for how each quantity is computed.

| onset | anti | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|---|
| r150 | - | cov (us-them) | +0.58 | -0.19 | +0.09 | +0.39 | +0.39 | +0.45 | +0.56 |
| r200 | - | cov (us-them) ~avg | +0.51 | -0.19 | +0.02 | +0.31 | +0.35 | +0.40 | +0.49 |
| r250 | - | exp (us-them) | +0.37 | +0.18 | +0.10 | +0.26 | +0.34 | +0.34 | +0.26 |
| r250 | - | sla (us-them) | +0.60 | +0.03 | +0.08 | +0.23 | +0.49 | +0.56 | +0.58 |
| r300 | - | exp (us-them) ~avg | +0.36 | +0.18 | +0.10 | +0.23 | +0.32 | +0.35 | +0.33 |
| r300 | - | sla (us-them) ~avg | +0.61 | +0.03 | +0.06 | +0.16 | +0.34 | +0.48 | +0.60 |
| r350 | r50 | ecInf (us-them) | +0.54 | -0.35 | -0.31 | +0.18 | +0.19 | +0.36 | +0.48 |
| r350 | - | pol (us-them) | +0.53 | +0.21 | -0.09 | +0.13 | +0.28 | +0.37 | +0.50 |
| r400 | - | ec (us-them) | +0.57 | +0.12 | -0.18 | +0.14 | +0.25 | +0.40 | +0.54 |
| r400 | - | ecGain (us-them) | +0.51 | . | -0.20 | +0.14 | +0.23 | +0.33 | +0.47 |
| r400 | - | ecLoss (us-them) [inverted] | +0.58 | . | -0.12 | +0.02 | +0.20 | +0.38 | +0.54 |
| r400 | - | pol (us-them) ~avg | +0.48 | +0.21 | -0.00 | +0.07 | +0.20 | +0.30 | +0.45 |
| r400 | - | unitInf (us-them) | +0.54 | +0.27 | +0.29 | +0.22 | +0.23 | +0.30 | +0.45 |
| r450 | r50 | ecInf (us-them) ~avg | +0.55 | -0.35 | -0.39 | +0.16 | +0.19 | +0.29 | +0.50 |
| r500 | - | ec (us-them) ~avg | +0.47 | +0.12 | -0.14 | +0.11 | +0.12 | +0.23 | +0.42 |
| r500 | - | ecLoss (us-them) [inverted] ~avg | +0.48 | . | -0.12 | +0.03 | +0.06 | +0.21 | +0.42 |
| r500 | - | navMoves (us-them) | +0.45 | -0.01 | +0.07 | +0.04 | +0.12 | +0.23 | +0.39 |
| r500 | - | unitInf (us-them) ~avg | +0.45 | +0.27 | +0.29 | +0.23 | +0.24 | +0.27 | +0.38 |
| r550 | - | ecGain (us-them) ~avg | +0.39 | . | -0.20 | +0.09 | +0.11 | +0.19 | +0.34 |
| r550 | - | muc (us-them) | +0.43 | +0.00 | +0.06 | +0.12 | +0.07 | +0.18 | +0.36 |
| r600 | - | navMoves (us-them) ~avg | +0.36 | -0.01 | +0.06 | +0.05 | +0.09 | +0.16 | +0.31 |
| r700 | - | muc (us-them) ~avg | +0.31 | +0.00 | +0.04 | +0.10 | +0.07 | +0.11 | +0.25 |
| - | - | buff (us-them) | +0.27 | +0.18 | +0.09 | +0.21 | +0.17 | +0.03 | +0.12 |
| - | - | buff (us-them) ~avg | +0.28 | +0.18 | +0.09 | +0.20 | +0.24 | +0.20 | +0.18 |
| - | r700 | navAba (us-them) [inverted] | -0.36 | -0.36 | -0.02 | +0.09 | +0.07 | -0.03 | -0.24 |
| - | - | navAba (us-them) [inverted] ~avg | -0.36 | -0.36 | -0.05 | +0.06 | +0.07 | +0.03 | -0.13 |
| - | - | navSwamp (us-them) [inverted] | . | . | . | . | . | . | . |
| - | - | navSwamp (us-them) [inverted] ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

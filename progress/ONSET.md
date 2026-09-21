# Which metric starts predicting the result first

47 games, 37 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. **Onset** is the first round where the correlation reaches *+*threshold and holds.
**Anti** is the first round where it reaches *-*threshold and holds: there the metric predicts the result
backwards, which means either the orientation is wrong or something counter-intuitive is happening early.
A metric with an early anti and a late onset is changing sign, not rising early.
See `progress/METRICS.md` for how each quantity is computed.

| onset | anti | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|---|
| r150 | - | cov (us-them) | +0.72 | +0.11 | +0.30 | +0.45 | +0.51 | +0.59 | +0.65 |
| r150 | - | cov (us-them) ~avg | +0.61 | +0.11 | +0.26 | +0.40 | +0.43 | +0.52 | +0.57 |
| r150 | - | ec (us-them) | +0.82 | . | +0.22 | +0.56 | +0.47 | +0.62 | +0.74 |
| r150 | - | ec (us-them) ~avg | +0.73 | . | +0.22 | +0.52 | +0.49 | +0.57 | +0.68 |
| r150 | - | ecGain (us-them) | +0.85 | . | +0.22 | +0.51 | +0.46 | +0.63 | +0.76 |
| r150 | - | ecGain (us-them) ~avg | +0.72 | . | +0.22 | +0.48 | +0.46 | +0.54 | +0.67 |
| r200 | - | ecLoss (us-them) [inverted] | +0.67 | . | . | +0.44 | +0.32 | +0.41 | +0.60 |
| r200 | - | ecLoss (us-them) [inverted] ~avg | +0.55 | . | . | +0.40 | +0.34 | +0.44 | +0.48 |
| r200 | - | muc (us-them) | +0.71 | +0.21 | +0.23 | +0.39 | +0.47 | +0.60 | +0.60 |
| r200 | - | muc (us-them) ~avg | +0.61 | +0.21 | +0.23 | +0.32 | +0.39 | +0.50 | +0.56 |
| r200 | - | sla (us-them) | +0.69 | +0.04 | +0.08 | +0.40 | +0.62 | +0.66 | +0.67 |
| r250 | - | pol (us-them) | +0.68 | -0.02 | +0.16 | +0.26 | +0.37 | +0.53 | +0.63 |
| r250 | - | pol (us-them) ~avg | +0.59 | -0.02 | +0.10 | +0.23 | +0.28 | +0.43 | +0.52 |
| r250 | - | sla (us-them) ~avg | +0.67 | +0.04 | +0.07 | +0.26 | +0.43 | +0.60 | +0.64 |
| r300 | - | buff (us-them) ~avg | +0.34 | +0.03 | -0.15 | +0.19 | +0.34 | +0.27 | +0.23 |
| r300 | - | exp (us-them) | +0.35 | +0.00 | -0.02 | +0.28 | +0.35 | +0.25 | +0.23 |
| r350 | - | navMoves (us-them) | +0.57 | +0.05 | +0.14 | +0.22 | +0.21 | +0.40 | +0.50 |
| r350 | - | unitInf (us-them) | +0.69 | +0.12 | +0.01 | +0.03 | +0.19 | +0.54 | +0.66 |
| r400 | - | ecInf (us-them) | +0.63 | -0.30 | -0.08 | +0.11 | +0.20 | +0.46 | +0.60 |
| r400 | - | unitInf (us-them) ~avg | +0.64 | +0.12 | +0.03 | +0.02 | +0.07 | +0.31 | +0.58 |
| r450 | - | ecInf (us-them) ~avg | +0.61 | -0.30 | -0.17 | +0.06 | +0.11 | +0.26 | +0.55 |
| r450 | - | navMoves (us-them) ~avg | +0.49 | +0.05 | +0.13 | +0.20 | +0.13 | +0.29 | +0.43 |
| - | - | buff (us-them) | +0.37 | +0.03 | -0.15 | +0.18 | +0.37 | +0.16 | +0.06 |
| - | - | exp (us-them) ~avg | +0.29 | +0.00 | -0.02 | +0.22 | +0.28 | +0.29 | +0.22 |
| - | r550 | navAba (us-them) [inverted] | -0.43 | +0.07 | -0.06 | -0.09 | -0.05 | -0.19 | -0.35 |
| - | r700 | navAba (us-them) [inverted] ~avg | -0.33 | +0.07 | -0.04 | -0.08 | +0.03 | -0.09 | -0.26 |
| - | - | navSwamp (us-them) [inverted] | . | . | . | . | . | . | . |
| - | - | navSwamp (us-them) [inverted] ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

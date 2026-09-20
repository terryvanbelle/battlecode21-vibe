# Which metric starts predicting the result first

46 games, 33 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. **Onset** is the first round where the correlation reaches *+*threshold and holds.
**Anti** is the first round where it reaches *-*threshold and holds: there the metric predicts the result
backwards, which means either the orientation is wrong or something counter-intuitive is happening early.
A metric with an early anti and a late onset is changing sign, not rising early.
See `progress/METRICS.md` for how each quantity is computed.

| onset | anti | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|---|
| r50 | - | unitInf (us-them) | +0.50 | +0.36 | +0.28 | +0.23 | +0.14 | +0.31 | +0.50 |
| r50 | - | unitInf (us-them) ~avg | +0.40 | +0.36 | +0.30 | +0.29 | +0.18 | +0.23 | +0.38 |
| r100 | - | cov (us-them) | +0.66 | +0.24 | +0.39 | +0.60 | +0.66 | +0.64 | +0.63 |
| r100 | - | cov (us-them) ~avg | +0.64 | +0.24 | +0.37 | +0.56 | +0.61 | +0.63 | +0.62 |
| r100 | - | muc (us-them) | +0.55 | +0.30 | +0.42 | +0.55 | +0.48 | +0.49 | +0.53 |
| r100 | - | muc (us-them) ~avg | +0.52 | +0.30 | +0.39 | +0.52 | +0.52 | +0.48 | +0.49 |
| r100 | - | navMoves (us-them) | +0.55 | +0.21 | +0.41 | +0.53 | +0.51 | +0.50 | +0.55 |
| r100 | - | navMoves (us-them) ~avg | +0.53 | +0.21 | +0.38 | +0.53 | +0.52 | +0.50 | +0.51 |
| r100 | - | pol (us-them) | +0.68 | +0.21 | +0.41 | +0.55 | +0.66 | +0.68 | +0.68 |
| r100 | - | pol (us-them) ~avg | +0.65 | +0.21 | +0.36 | +0.54 | +0.62 | +0.64 | +0.65 |
| r150 | - | buff (us-them) | +0.33 | +0.12 | +0.26 | +0.31 | +0.31 | +0.20 | +0.31 |
| r150 | - | buff (us-them) ~avg | +0.35 | +0.12 | +0.26 | +0.35 | +0.30 | +0.32 | +0.35 |
| r150 | - | ec (us-them) ~avg | +0.60 | -0.01 | +0.15 | +0.47 | +0.54 | +0.59 | +0.60 |
| r150 | - | ecGain (us-them) | +0.65 | . | +0.17 | +0.52 | +0.56 | +0.64 | +0.64 |
| r150 | - | ecGain (us-them) ~avg | +0.60 | . | +0.20 | +0.48 | +0.54 | +0.58 | +0.60 |
| r150 | - | exp (us-them) | +0.56 | +0.14 | +0.26 | +0.34 | +0.37 | +0.49 | +0.56 |
| r150 | - | exp (us-them) ~avg | +0.53 | +0.14 | +0.26 | +0.33 | +0.31 | +0.40 | +0.49 |
| r200 | - | ec (us-them) | +0.67 | -0.01 | +0.17 | +0.54 | +0.55 | +0.65 | +0.62 |
| r200 | - | ecInf (us-them) | +0.62 | +0.13 | +0.28 | +0.36 | +0.40 | +0.46 | +0.60 |
| r200 | - | ecInf (us-them) ~avg | +0.64 | +0.14 | +0.32 | +0.33 | +0.39 | +0.46 | +0.61 |
| r200 | - | ecLoss (us-them) [inverted] | +0.52 | . | . | +0.39 | +0.34 | +0.52 | +0.48 |
| r250 | - | ecLoss (us-them) [inverted] ~avg | +0.45 | . | . | +0.27 | +0.33 | +0.45 | +0.44 |
| r250 | - | sla (us-them) | +0.67 | +0.09 | +0.13 | +0.29 | +0.44 | +0.53 | +0.67 |
| r250 | - | sla (us-them) ~avg | +0.58 | +0.10 | +0.12 | +0.26 | +0.36 | +0.43 | +0.57 |
| - | r350 | navAba (us-them) [inverted] | -0.52 | -0.00 | -0.17 | -0.12 | -0.23 | -0.28 | -0.47 |
| - | r550 | navAba (us-them) [inverted] ~avg | -0.45 | -0.03 | -0.17 | -0.14 | -0.20 | -0.20 | -0.36 |
| - | - | navSwamp (us-them) [inverted] | . | . | . | . | . | . | . |
| - | - | navSwamp (us-them) [inverted] ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

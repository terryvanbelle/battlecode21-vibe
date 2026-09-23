# Which metric starts predicting the result first

114 games, 103 wins. Noise floor about 0.19; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. **Onset** is the first round where the correlation reaches *+*threshold and holds.
**Anti** is the first round where it reaches *-*threshold and holds: there the metric predicts the result
backwards, which means either the orientation is wrong or something counter-intuitive is happening early.
A metric with an early anti and a late onset is changing sign, not rising early.
See `progress/METRICS.md` for how each quantity is computed.

| onset | anti | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|---|
| r50 | - | pol (us-them) ~avg | +0.57 | +0.31 | +0.28 | +0.33 | +0.39 | +0.45 | +0.54 |
| r150 | - | cov (us-them) | +0.68 | +0.26 | +0.26 | +0.47 | +0.55 | +0.60 | +0.66 |
| r150 | - | cov (us-them) ~avg | +0.63 | +0.27 | +0.28 | +0.42 | +0.53 | +0.58 | +0.62 |
| r150 | - | ec (us-them) | +0.72 | +0.08 | +0.13 | +0.28 | +0.50 | +0.59 | +0.67 |
| r150 | - | muc (us-them) | +0.70 | +0.03 | +0.18 | +0.44 | +0.48 | +0.53 | +0.63 |
| r200 | - | ec (us-them) ~avg | +0.66 | +0.08 | +0.14 | +0.32 | +0.44 | +0.54 | +0.62 |
| r200 | - | muc (us-them) ~avg | +0.62 | +0.03 | +0.13 | +0.37 | +0.45 | +0.50 | +0.58 |
| r200 | - | navMoves (us-them) | +0.59 | +0.17 | +0.21 | +0.32 | +0.42 | +0.46 | +0.56 |
| r200 | - | pol (us-them) | +0.67 | +0.31 | +0.22 | +0.32 | +0.43 | +0.50 | +0.61 |
| r200 | - | sla (us-them) | +0.70 | -0.05 | +0.02 | +0.31 | +0.46 | +0.57 | +0.67 |
| r250 | - | buff (us-them) | +0.39 | +0.15 | +0.08 | +0.21 | +0.39 | +0.22 | +0.07 |
| r250 | - | ecGain (us-them) | +0.73 | . | +0.11 | +0.28 | +0.49 | +0.59 | +0.68 |
| r250 | - | ecGain (us-them) ~avg | +0.64 | . | +0.11 | +0.30 | +0.42 | +0.51 | +0.60 |
| r250 | - | exp (us-them) | +0.50 | +0.14 | +0.12 | +0.19 | +0.46 | +0.47 | +0.37 |
| r250 | - | navMoves (us-them) ~avg | +0.54 | +0.17 | +0.20 | +0.29 | +0.39 | +0.45 | +0.52 |
| r300 | - | buff (us-them) ~avg | +0.42 | +0.15 | +0.11 | +0.18 | +0.41 | +0.40 | +0.31 |
| r300 | - | ecLoss (us-them) [inverted] | +0.61 | . | . | +0.11 | +0.35 | +0.46 | +0.50 |
| r300 | - | exp (us-them) ~avg | +0.43 | +0.15 | +0.13 | +0.15 | +0.31 | +0.41 | +0.42 |
| r300 | - | sla (us-them) ~avg | +0.65 | -0.05 | -0.01 | +0.18 | +0.36 | +0.49 | +0.61 |
| r350 | - | ecLoss (us-them) [inverted] ~avg | +0.53 | . | . | +0.15 | +0.27 | +0.42 | +0.46 |
| r350 | - | unitInf (us-them) | +0.68 | +0.13 | +0.10 | +0.14 | +0.23 | +0.44 | +0.65 |
| r400 | - | ecInf (us-them) | +0.61 | -0.13 | -0.03 | -0.03 | +0.18 | +0.42 | +0.58 |
| r400 | - | unitInf (us-them) ~avg | +0.60 | +0.13 | +0.10 | +0.12 | +0.18 | +0.31 | +0.53 |
| r450 | - | ecInf (us-them) ~avg | +0.56 | -0.13 | -0.05 | -0.01 | +0.11 | +0.29 | +0.53 |
| - | r350 | navAba (us-them) [inverted] | -0.56 | +0.12 | +0.09 | +0.05 | -0.24 | -0.38 | -0.53 |
| - | r450 | navAba (us-them) [inverted] ~avg | -0.48 | +0.12 | +0.10 | +0.09 | -0.11 | -0.28 | -0.45 |
| - | - | navSwamp (us-them) [inverted] | . | . | . | . | . | . | . |
| - | - | navSwamp (us-them) [inverted] ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

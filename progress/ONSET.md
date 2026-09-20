# Which metric starts predicting the result first

48 games, 13 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. Onset is the first round where the correlation reaches the threshold and holds.
See `progress/METRICS.md` for how each quantity is computed.

| onset | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|
| r50 | unitInf (us-them) | +0.68 | +0.37 | +0.37 | +0.44 | +0.46 | +0.53 | +0.55 |
| r50 | unitInf (us-them) ~avg | +0.64 | +0.37 | +0.38 | +0.46 | +0.46 | +0.51 | +0.54 |
| r150 | ecInf (us-them) | +0.57 | -0.26 | -0.15 | +0.49 | +0.47 | +0.49 | +0.49 |
| r200 | cov (us-them) | +0.58 | +0.02 | +0.06 | +0.42 | +0.49 | +0.52 | +0.57 |
| r200 | cov (us-them) ~avg | +0.53 | +0.02 | +0.05 | +0.34 | +0.43 | +0.44 | +0.51 |
| r200 | ec (us-them) | +0.59 | -0.13 | +0.16 | +0.45 | +0.53 | +0.59 | +0.56 |
| r200 | ec (us-them) ~avg | +0.63 | -0.13 | +0.11 | +0.38 | +0.46 | +0.54 | +0.59 |
| r200 | ecInf (us-them) ~avg | +0.67 | -0.26 | -0.20 | +0.44 | +0.51 | +0.60 | +0.64 |
| r200 | navAba (us-them) | -0.41 | -0.27 | -0.16 | -0.33 | -0.38 | -0.15 | +0.18 |
| r250 | navAba (us-them) ~avg | -0.38 | -0.27 | -0.19 | -0.28 | -0.38 | -0.26 | -0.00 |
| r300 | muc (us-them) | +0.42 | +0.12 | +0.13 | +0.25 | +0.33 | +0.35 | +0.40 |
| r350 | pol (us-them) | +0.54 | +0.20 | -0.08 | -0.02 | +0.29 | +0.40 | +0.46 |
| r350 | sla (us-them) | +0.55 | -0.19 | +0.00 | +0.11 | +0.28 | +0.37 | +0.46 |
| r400 | navMoves (us-them) | +0.47 | +0.03 | +0.14 | +0.12 | +0.19 | +0.31 | +0.45 |
| r450 | muc (us-them) ~avg | +0.38 | +0.12 | +0.13 | +0.22 | +0.27 | +0.29 | +0.36 |
| r450 | pol (us-them) ~avg | +0.49 | +0.20 | +0.01 | -0.04 | +0.12 | +0.27 | +0.40 |
| r450 | sla (us-them) ~avg | +0.51 | -0.19 | -0.07 | +0.02 | +0.14 | +0.27 | +0.38 |
| r500 | navMoves (us-them) ~avg | +0.42 | +0.03 | +0.12 | +0.13 | +0.15 | +0.24 | +0.38 |
| - | buff (us-them) | +0.30 | +0.22 | -0.17 | -0.09 | +0.27 | +0.02 | +0.15 |
| - | buff (us-them) ~avg | +0.22 | +0.22 | -0.04 | +0.04 | +0.07 | +0.11 | -0.01 |
| - | exp (us-them) | +0.27 | +0.23 | +0.10 | -0.09 | +0.13 | +0.24 | +0.23 |
| - | exp (us-them) ~avg | +0.23 | +0.23 | +0.14 | -0.06 | +0.01 | +0.15 | +0.18 |
| - | navSwamp (us-them) | . | . | . | . | . | . | . |
| - | navSwamp (us-them) ~avg | . | . | . | . | . | . | . |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

# Which metric starts predicting the result first

48 games, 13 wins. Noise floor about 0.29; a correlation inside that band is not evidence.

Every metric is oriented so **higher is better for us**, so a positive correlation always means
"this being better goes with winning". `~avg` is the running mean over all rounds so far rather than
the snapshot at that round. Onset is the first round where the correlation reaches the threshold and holds.
See `progress/METRICS.md` for how each quantity is computed.

| onset | metric | peak corr | r50 | r100 | r200 | r300 | r400 | r600 |
|---|---|---|---|---|---|---|---|---|
| r50 | cov | -0.46 | -0.39 | -0.40 | -0.42 | -0.44 | -0.36 | -0.20 |
| r50 | cov ~avg | -0.45 | -0.39 | -0.40 | -0.42 | -0.45 | -0.42 | -0.31 |
| r50 | pol | -0.45 | -0.45 | -0.42 | -0.34 | -0.22 | -0.05 | +0.13 |
| r50 | pol ~avg | -0.45 | -0.45 | -0.43 | -0.39 | -0.34 | -0.24 | -0.08 |
| r50 | unitInf (us-them) | +0.68 | +0.37 | +0.37 | +0.44 | +0.46 | +0.53 | +0.55 |
| r50 | unitInf (us-them) ~avg | +0.64 | +0.37 | +0.38 | +0.46 | +0.46 | +0.51 | +0.54 |
| r100 | ecInf | +0.54 | +0.20 | +0.35 | +0.47 | +0.43 | +0.52 | +0.35 |
| r100 | ecInf ~avg | +0.63 | +0.20 | +0.34 | +0.46 | +0.50 | +0.54 | +0.61 |
| r100 | navMoves | -0.44 | -0.26 | -0.30 | -0.39 | -0.43 | -0.30 | -0.12 |
| r100 | navMoves ~avg | -0.44 | -0.26 | -0.31 | -0.38 | -0.44 | -0.37 | -0.25 |
| r150 | ecInf (us-them) | +0.57 | -0.26 | -0.15 | +0.49 | +0.47 | +0.49 | +0.49 |
| r150 | unitInf | +0.45 | -0.05 | +0.14 | +0.32 | +0.26 | +0.34 | +0.44 |
| r200 | ec (us-them) | +0.59 | -0.13 | +0.16 | +0.45 | +0.53 | +0.59 | +0.56 |
| r200 | ec (us-them) ~avg | +0.63 | -0.13 | +0.11 | +0.38 | +0.46 | +0.54 | +0.59 |
| r200 | ecInf (us-them) ~avg | +0.67 | -0.26 | -0.20 | +0.44 | +0.51 | +0.60 | +0.64 |
| r200 | navMoves (us-them) | -0.41 | -0.27 | -0.16 | -0.33 | -0.38 | -0.15 | +0.18 |
| r200 | navSwamp (us-them) | -0.32 | -0.18 | -0.15 | -0.31 | -0.32 | -0.05 | +0.08 |
| r250 | navMoves (us-them) ~avg | -0.38 | -0.27 | -0.19 | -0.28 | -0.38 | -0.26 | -0.00 |
| r300 | buff ~avg | +0.36 | +0.15 | +0.05 | +0.24 | +0.32 | +0.18 | -0.02 |
| r300 | muc (us-them) | +0.42 | +0.12 | +0.13 | +0.25 | +0.33 | +0.35 | +0.40 |
| r300 | navSwamp (us-them) ~avg | -0.32 | -0.18 | -0.19 | -0.25 | -0.32 | -0.22 | -0.00 |
| r350 | ec | +0.37 | +0.14 | +0.14 | +0.23 | +0.25 | +0.32 | +0.32 |
| r350 | pol (us-them) | +0.54 | +0.20 | -0.08 | -0.02 | +0.29 | +0.40 | +0.46 |
| r350 | sla (us-them) | +0.55 | -0.19 | +0.00 | +0.11 | +0.28 | +0.37 | +0.46 |
| r350 | unitInf ~avg | +0.41 | -0.05 | +0.11 | +0.28 | +0.28 | +0.32 | +0.39 |
| r400 | cov (us-them) | +0.47 | +0.03 | +0.14 | +0.12 | +0.19 | +0.31 | +0.45 |
| r450 | muc (us-them) ~avg | +0.38 | +0.12 | +0.13 | +0.22 | +0.27 | +0.29 | +0.36 |
| r450 | pol (us-them) ~avg | +0.49 | +0.20 | +0.01 | -0.04 | +0.12 | +0.27 | +0.40 |
| r450 | sla (us-them) ~avg | +0.51 | -0.19 | -0.07 | +0.02 | +0.14 | +0.27 | +0.38 |
| r500 | cov (us-them) ~avg | +0.42 | +0.03 | +0.12 | +0.13 | +0.15 | +0.24 | +0.38 |
| r700 | navSwamp [inverted] | +0.31 | -0.13 | -0.07 | +0.17 | +0.23 | +0.23 | +0.29 |
| - | buff | +0.22 | +0.15 | -0.04 | +0.17 | +0.22 | -0.05 | +0.10 |
| - | buff (us-them) | +0.30 | +0.22 | -0.17 | -0.09 | +0.27 | +0.02 | +0.15 |
| - | buff (us-them) ~avg | +0.22 | +0.22 | -0.04 | +0.04 | +0.07 | +0.11 | -0.01 |
| - | ec ~avg | +0.29 | +0.14 | +0.15 | +0.15 | +0.17 | +0.21 | +0.28 |
| - | exp | +0.24 | +0.14 | +0.02 | +0.13 | +0.20 | +0.11 | +0.02 |
| - | exp (us-them) | +0.27 | +0.23 | +0.10 | -0.09 | +0.13 | +0.24 | +0.23 |
| - | exp (us-them) ~avg | +0.23 | +0.23 | +0.14 | -0.06 | +0.01 | +0.15 | +0.18 |
| - | exp ~avg | +0.18 | +0.14 | +0.07 | +0.13 | +0.15 | +0.17 | +0.06 |
| - | muc | -0.22 | +0.16 | +0.02 | -0.20 | -0.17 | -0.07 | -0.03 |
| - | muc ~avg | -0.19 | +0.16 | +0.10 | -0.06 | -0.19 | -0.17 | -0.12 |
| - | navAba (us-them) | . | . | . | . | . | . | . |
| - | navAba (us-them) ~avg | . | . | . | . | . | . | . |
| - | navAba [inverted] | . | . | . | . | . | . | . |
| - | navAba [inverted] ~avg | . | . | . | . | . | . | . |
| - | navSwamp [inverted] ~avg | +0.28 | -0.13 | -0.09 | +0.08 | +0.18 | +0.20 | +0.25 |
| - | sla | -0.31 | -0.31 | -0.15 | -0.15 | -0.15 | -0.03 | +0.17 |
| - | sla ~avg | -0.31 | -0.31 | -0.22 | -0.20 | -0.21 | -0.15 | -0.03 |

**Reading it.** Earliest onset is the first place to look: temporal precedence is the one causal hint a
correlation can honestly give. Late-onset metrics are usually the scoreboard rather than the cause -- by then
the winner leads on everything. A high correlation earns a diagnostic game, not a code change.

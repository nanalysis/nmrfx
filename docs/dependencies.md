## Internal dependencies flow

```mermaid
graph RL
  processor-gui --> processor --> core
  structure --> core

  analyst-gui --> analyst --> processor
  analyst --> structure

  processor-gui --> utils

  analyst-gui --> processor-gui
  
  analyst-gui --Not included in NmrFX--> ringnmr-gui --> ringnmr
  ringnmr --> core
  ringnmr-gui --> utils
```

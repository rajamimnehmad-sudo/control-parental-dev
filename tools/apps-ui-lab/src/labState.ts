export type LabMode = "usuario" | "admin";
export type ViewportMode = "android" | "iphone" | "compare";
export type VariantMode = "minimal" | "professional" | "modern";
export type LabStateKey = "connectedProtected" | "connectedUnprotected" | "disconnected" | "warning" | "licenseExpired" | "loading" | "error" | "empty";
export type SharedState = { protectionState: "active" | "inactive"; connectionState: "connected" | "disconnected"; licenseState: "active" | "warning" | "expired"; warningCount: number; deviceCount: number; pendingAction: string; lastUpdated: string; loading?: boolean; error?: string | null; isEmpty?: boolean };
export const labScenarios: Record<LabStateKey, SharedState> = {
 connectedProtected:{protectionState:"active",connectionState:"connected",licenseState:"active",warningCount:0,deviceCount:1,pendingAction:"Todo estable",lastUpdated:"hace 8 s"},
 connectedUnprotected:{protectionState:"inactive",connectionState:"connected",licenseState:"active",warningCount:1,deviceCount:1,pendingAction:"Reactivar cobertura en este dispositivo",lastUpdated:"hace 12 s"},
 disconnected:{protectionState:"inactive",connectionState:"disconnected",licenseState:"active",warningCount:2,deviceCount:0,pendingAction:"Conectar app para sincronizar estado",lastUpdated:"hace 4 min"},
 warning:{protectionState:"active",connectionState:"connected",licenseState:"warning",warningCount:2,deviceCount:2,pendingAction:"Actualizar reglas para el horario escolar",lastUpdated:"hace 35 s"},
 licenseExpired:{protectionState:"inactive",connectionState:"connected",licenseState:"expired",warningCount:4,deviceCount:2,pendingAction:"Renovar licencia para continuar protección",lastUpdated:"hace 1 h"},
 loading:{protectionState:"inactive",connectionState:"disconnected",licenseState:"active",warningCount:0,deviceCount:0,pendingAction:"Sincronizando configuración",lastUpdated:"hace 2 s",loading:true,isEmpty:false},
 error:{protectionState:"inactive",connectionState:"disconnected",licenseState:"warning",warningCount:1,deviceCount:1,pendingAction:"Reintentar inicialización",lastUpdated:"hace 9 s",error:"No pudimos leer el estado del dispositivo"},
 empty:{protectionState:"inactive",connectionState:"connected",licenseState:"active",warningCount:0,deviceCount:0,pendingAction:"Vincular primer dispositivo para empezar",lastUpdated:"hace 30 s",isEmpty:true}
};

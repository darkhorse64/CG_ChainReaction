import { GraphicEntityModule } from './entity-module/GraphicEntityModule.js';
import { ToggleModule } from './toggle-module/ToggleModule.js'
import { EndScreenModule } from './endscreen-module/EndScreenModule.js';

// List of viewer modules that you want to use in your game
export const modules = [
	GraphicEntityModule,
	ToggleModule,
	EndScreenModule
];

export const gameName = 'Chain Reaction';

// The list of toggles displayed in the options of the viewer
export const options = [
    ToggleModule.defineToggle({
        toggle: 'debugToggle',
        title: 'DEBUG',
        values: {
            'ON': true,
            'OFF': false
        },
        default: true
    })
];

export const playerColors = [
	'#ff1d5c', // radical red
	'#22a1e4', // curious blue
	'#6ac371', // mantis green
	'#ff8f16', // west side orange
	'#9975e2', // medium purple
	'#3ac5ca', // scooter blue
	'#de6ddf', // lavender pink
	'#ff0000'  // solid red
  ];